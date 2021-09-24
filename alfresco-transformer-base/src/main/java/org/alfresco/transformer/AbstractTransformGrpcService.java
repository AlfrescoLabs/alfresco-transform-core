package org.alfresco.transformer;

import com.google.protobuf.ByteString;
import net.devh.boot.grpc.server.service.GrpcService;
import org.alfresco.transform.client.model.TransformRequestValidator;
import org.alfresco.transform.client.registry.TransformServiceRegistry;
import org.alfresco.transform.exceptions.TransformException;
import org.alfresco.transformer.clients.AlfrescoSharedFileStoreClient;
import org.alfresco.transformer.fs.FileManager;
import org.alfresco.transformer.grpc.MultipartFile;
import org.alfresco.transformer.grpc.TransformReply;
import org.alfresco.transformer.grpc.TransformServiceGrpc;
import org.alfresco.transformer.logging.LogEntry;
import org.alfresco.transformer.probes.ProbeTestTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.alfresco.transformer.fs.FileManager.createTargetFileName;
import static org.alfresco.transformer.util.RequestParamMap.SOURCE_ENCODING;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INSUFFICIENT_STORAGE;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.util.StringUtils.getFilename;

@GrpcService
public abstract class AbstractTransformGrpcService extends TransformServiceGrpc.TransformServiceImplBase
{


    @Autowired
    private TransformServiceRegistry transformRegistry;

    private static final Logger logger = LoggerFactory.getLogger(
                AbstractTransformGrpcService.class);


    public abstract String getTransformerName();


    public abstract String version();

    public abstract  void transform(org.alfresco.transformer.grpc.TransformRequest request,
                io.grpc.stub.StreamObserver<org.alfresco.transformer.grpc.TransformReply> responseObserver);

    public void transform(org.alfresco.transformer.grpc.TransformRequest request,
                io.grpc.stub.StreamObserver<org.alfresco.transformer.grpc.TransformReply> responseObserver, TransformServiceRegistry transformRegistry) {


        this.transformRegistry = transformRegistry;

        logger.info("inside grpc transform service, request " + request.getOriginalFileName());
        final String targetFilename = createTargetFileName(
                    request.getOriginalFileName(), request.getTargetExtension());
        getProbeTestTransform().incrementTransformerCount();
        final File sourceFile = createSourceFile(request);
        final File targetFile = createTargetFile(targetFilename);

        Map<String, String> transformOptions = new HashMap<>(request.getTransformRequestOptionsMap());
       String transformName = getTransformerName(request.getSourceMimeType(), request.getTargetMimeType(),null, sourceFile, transformOptions);
       transformImpl(transformName, request.getSourceMimeType(), request.getTargetMimeType(), transformOptions, sourceFile, targetFile);

        LogEntry.setTargetSize(targetFile.length());
        long time = LogEntry.setStatusCodeAndMessage(OK.value(), "Success");
        time += LogEntry.addDelay(0l);
        getProbeTestTransform().recordTransformTime(time);

        Resource resource = load(targetFile);
        TransformReply reply = TransformReply.newBuilder().setRequestId(request.getRequestId()).setFile(loadFrom(resource)).build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();


    }

    private static Resource load(File file)
    {
        try
        {
            Resource resource = new UrlResource(file.toURI());
            if (resource.exists() || resource.isReadable())
            {
                return resource;
            }
            else
            {
                throw new TransformException(INTERNAL_SERVER_ERROR.value(),
                            "Could not read the target file: " + file.getPath());
            }
        }
        catch (MalformedURLException e)
        {
            throw new TransformException(INTERNAL_SERVER_ERROR.value(),
                        "The target filename was malformed: " + file.getPath(), e);
        }
    }
    private ByteString loadFrom(Resource resource)
    {
        ByteString byteString = null;


        try
        {
            byteString = ByteString.readFrom(resource.getInputStream());
        }
        catch (IOException e)
        {
            throw new TransformException(INTERNAL_SERVER_ERROR.value(),
                        "The target filename was malformed: " + resource.getFilename(), e);
        }
        return byteString;
    }
    private String getTransformerName(String sourceMimetype, String targetMimetype,
                String requestTransformName, File sourceFile,
                Map<String, String> transformOptions)
    {
        // Check if transformName was provided in the request (this can happen for ACS legacy transformers)
        String transformName = requestTransformName;
        if (transformName == null || transformName.isEmpty())
        {
            transformName = getTransformerName(sourceFile, sourceMimetype, targetMimetype, transformOptions);
        }
        else if (logger.isInfoEnabled())
        {
            logger.info("Using transform name provided in the request: " + requestTransformName);
        }
        return transformName;
    }

    protected String getTransformerName(final File sourceFile, final String sourceMimetype,
                final String targetMimetype, final Map<String, String> transformOptions)
    {
        // The transformOptions always contains sourceEncoding when sent to a T-Engine, even though it should not be
        // used to select a transformer. Similar to source and target mimetypes and extensions, but these are not
        // passed in transformOptions.

        String sourceEncoding = transformOptions.remove(SOURCE_ENCODING);
        try
        {
            final long sourceSizeInBytes = sourceFile.length();
            logger.info("sourceMimetype, sourceSizeInBytes, targetMimetype:" + sourceMimetype + ", " + sourceSizeInBytes + ", " + targetMimetype
            );
            final String transformerName = transformRegistry.findTransformerName(sourceMimetype, sourceSizeInBytes,
                        targetMimetype, transformOptions, null);
            if (transformerName == null)
            {
                throw new TransformException(BAD_REQUEST.value(), "No transforms were able to handle the request");
            }
            return transformerName;
        }
        finally
        {
            if (sourceEncoding != null)
            {
                transformOptions.put(SOURCE_ENCODING, sourceEncoding);
            }
        }
    }
    /**
     * Returns a File that holds the source content for a transformation.
     *
     * @param request
     * @return a temporary File.
     * @throws TransformException if there was no source filename.
     */
    public static File createSourceFile(org.alfresco.transformer.grpc.TransformRequest request)
    {
        MultipartFile multipartFile = request.getFile();
        String filename = multipartFile.getOrginalFileName();
        long size = multipartFile.getSize();
        filename = checkFilename(true, filename);
        File file = FileManager.TempFileProvider.createTempFile("source_", "_" + filename);

        save(multipartFile, file);
        LogEntry.setSource(filename, size);
        return file;
    }

    private static void save(MultipartFile multipartFile, File file)
    {
        try
        {

            Files.copy(multipartFile.getFile().newInput(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            throw new TransformException(INSUFFICIENT_STORAGE.value(),
                        "Failed to store the source file", e);
        }
    }


    /**
     * Checks the filename is okay to uses in a temporary file name.
     *
     * @param filename or path to be checked.
     * @return the filename part of the supplied filename if it was a path.
     * @throws TransformException if there was no target filename.
     */
    private static String checkFilename(boolean source, String filename)
    {
        filename = getFilename(filename);
        if (filename == null || filename.isEmpty())
        {
            String sourceOrTarget = source ? "source" : "target";
            int statusCode = source ? BAD_REQUEST.value() : INTERNAL_SERVER_ERROR.value();
            throw new TransformException(statusCode,
                        "The " + sourceOrTarget + " filename was not supplied");
        }
        return filename;
    }

    /**
     * Returns a File to be used to store the result of a transformation.
     *

     * @param filename The targetFilename supplied in the request. Only the filename if a path is used as part of the
     *                 temporary filename.
     * @return a temporary File.
     * @throws TransformException if there was no target filename.
     */
    public static File createTargetFile(String filename)
    {
        File file = buildFile(filename);
       // request.setAttribute(TARGET_FILE, file);
        return file;
    }

    public static File buildFile(String filename)
    {
        filename = checkFilename(false, filename);
        LogEntry.setTarget(filename);
        return FileManager.TempFileProvider.createTempFile("target_", "_" + filename);
    }

    public static void deleteFile(final File file) throws Exception
    {
        if (!file.delete())
        {
            throw new Exception("Failed to delete file");
        }
    }

    abstract void transformImpl(String transformName, String sourceMimetype, String targetMimetype,
                Map<String, String> transformOptions, File sourceFile, File targetFile);


    /**
     * Provides the Kubernetes pod probes.
     */
    abstract ProbeTestTransform getProbeTestTransform();
}
