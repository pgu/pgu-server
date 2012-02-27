package pgu;

public class RequestContext {

    public enum HttpMethod {
        GET, POST, PUT
    }

    public HttpMethod method;
    public boolean askForXml;
    public String response = "";

}
