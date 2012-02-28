package pgu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RequestContext {

    public enum HttpMethod {
        GET, POST, PUT
    }

    public enum ContentType {
        ANY("*/*"), //
        BINARY("application/octet-stream"), //
        HTML("text/html"), //
        JSON("application/json", "application/javascript", "text/javascript"), //
        TEXT("text/plain"), //
        URLENC("application/x-www-form-urlencoded"), //
        XML("application/xml", "text/xml", "application/xhtml+xml", "application/atom+xml");

        private final List<String> values = new ArrayList<String>();

        private ContentType(final String v, final String... values) {
            this.values.add(v);

            if (null != values) {
                this.values.addAll(Arrays.asList(values));
            }
        }

        public static ContentType extractContentTypeFromHeader(final String line) {
            return null;
        }
    }

    public HttpMethod  method;
    public ContentType contentType = ContentType.ANY;
    public String      response    = "";

}
