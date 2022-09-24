package model.Forum;

import java.util.ArrayList;
import java.util.List;
public class Pictures {


    private List<String> files = new ArrayList<>() ;
    private Long postId ;

    public Pictures(){

    }


    public Pictures(List<String> files, Long postId) {
        this.files = files;
        this.postId = postId;
    }
    public List<String> getFiles() {
        return files;
    }
    public void setFiles(List<String> files) {
        this.files = files;
    }
    public Long getPostId() {
        return postId;
    }
    public void setPostId(Long postId) {
        this.postId = postId;
    }


}
