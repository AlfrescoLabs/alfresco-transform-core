package org.alfresco.transformer;

import net.devh.boot.grpc.server.service.GrpcService;
import org.alfresco.transformer.grpc.TransformServiceGrpc;

@GrpcService
public abstract class AbstractTransformGrpcService extends TransformServiceGrpc.TransformServiceImplBase
{


}
