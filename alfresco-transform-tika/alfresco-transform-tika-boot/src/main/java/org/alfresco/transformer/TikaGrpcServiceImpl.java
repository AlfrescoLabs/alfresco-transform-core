/*
 * #%L
 * Alfresco Transform Core
 * %%
 * Copyright (C) 2005 - 2019 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * -
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
 * -
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * -
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * -
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.transformer;

import net.devh.boot.grpc.server.service.GrpcService;
import org.alfresco.transformer.executors.TikaJavaExecutor;
import org.alfresco.transformer.probes.ProbeTestTransform;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.alfresco.transformer.executors.Tika.PDF_BOX;
import static org.alfresco.transformer.util.MimetypeMap.MIMETYPE_PDF;
import static org.alfresco.transformer.util.MimetypeMap.MIMETYPE_TEXT_PLAIN;
import static org.alfresco.transformer.util.RequestParamMap.TRANSFORM_NAME_PARAMETER;

@GrpcService
public class TikaGrpcServiceImpl extends AbstractTransformGrpcService
{

    private TikaJavaExecutor javaExecutor;

    public TikaGrpcServiceImpl(@Value("${transform.core.tika.pdfBox.notExtractBookmarksTextDefault:false}") boolean notExtractBookmarksTextDefault)
    {
        javaExecutor= new TikaJavaExecutor(notExtractBookmarksTextDefault);
    }

    @Override void transformImpl(String transformName, String sourceMimetype, String targetMimetype,
                Map<String, String> transformOptions, File sourceFile, File targetFile)
    {
        transformOptions.put(TRANSFORM_NAME_PARAMETER, transformName);
        javaExecutor.transform(sourceMimetype, targetMimetype, transformOptions, sourceFile, targetFile);

    }

    @Override
    public ProbeTestTransform getProbeTestTransform()
    {
        // See the Javadoc on this method and Probes.md for the choice of these values.
        // the livenessPercentage is a little large as Tika does tend to suffer from slow transforms that class with a gc.
        return new ProbeTestTransform( "quick.pdf", "quick.txt",
                    60, 16, 400, 10240, 60 * 30 + 1, 60 * 15 + 20)
        {
            @Override
            protected void executeTransformCommand(File sourceFile, File targetFile)
            {
                transformImpl(PDF_BOX, MIMETYPE_PDF, MIMETYPE_TEXT_PLAIN, new HashMap<>(), sourceFile, targetFile);
            }
        };
    }
}
