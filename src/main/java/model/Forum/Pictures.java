package model.Forum;

import org.jboss.resteasy.reactive.RestForm;
import io.netty.handler.codec.http.multipart.FileUpload;
public class Pictures {

    @RestForm("image")
    public FileUpload file;
}
