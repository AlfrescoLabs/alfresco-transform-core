/*
 * #%L
 * Alfresco Repository
 * %%
 * Copyright (C) 2005 - 2021 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.transformer;

import net.devh.boot.grpc.server.service.GrpcService;
import org.alfresco.transformer.executors.PdfRendererCommandExecutor;
import org.alfresco.transformer.probes.ProbeTestTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Collections;
import java.util.Map;

@GrpcService
public class AlfrescoPdfRendererGrpcServiceImpl extends AbstractTransformGrpcService
{
    private static final Logger logger = LoggerFactory.getLogger(
                AlfrescoPdfRendererGrpcServiceImpl.class);

    @Value("${transform.core.pdfrenderer.exe}")
    private String execPath;

    PdfRendererCommandExecutor commandExecutor;

    @PostConstruct
    private void init()
    {
        commandExecutor = new PdfRendererCommandExecutor(execPath);
    }

//    @Override
//    public String getTransformerName()
//    {
//        return "Alfresco PDF Renderer";
//    }
//
//    @Override
//    public String version()
//    {
//        return commandExecutor.version();
//    }

    @Override
    public ProbeTestTransform getProbeTestTransform()
    {
        // See the Javadoc on this method and Probes.md for the choice of these values.
        return new ProbeTestTransform( "quick.pdf", "quick.png",
                    7455, 1024, 150, 10240, 60 * 20 + 1, 60 * 15 - 15)
        {
            @Override
            protected void executeTransformCommand(File sourceFile, File targetFile)
            {
                transformImpl(null, null, null, Collections.emptyMap(), sourceFile, targetFile);
            }
        };
    }

    @Override
    protected String getTransformerName(final File sourceFile, final String sourceMimetype,
                final String targetMimetype, final Map<String, String> transformOptions)
    {
        return null; // does not matter what value is returned, as it is not used because there is only one.
    }

    @Override
    public void transformImpl(String transformName, String sourceMimetype, String targetMimetype,
                Map<String, String> transformOptions, File sourceFile, File targetFile)
    {
        commandExecutor.transform(sourceMimetype, targetMimetype, transformOptions, sourceFile, targetFile);
    }
}
