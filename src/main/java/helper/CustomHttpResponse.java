package helper;

public class CustomHttpResponse {

    int statuscode;
    String responseMessage;

    public CustomHttpResponse(){};

    public CustomHttpResponse(int statuscode, String responseMessage) {
        this.statuscode = statuscode;
        this.responseMessage = responseMessage;
    }

    public int getStatuscode() {
        return statuscode;
    }
    public void setStatuscode(int statuscode) {
        this.statuscode = statuscode;
    }
    public String getResponseMessage() {
        return responseMessage;
    }
    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }
}
