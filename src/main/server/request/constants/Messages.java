package request.constants;

public class Messages {
    public static final String GITHUB_RATE_LIMIT_MESSAGE = "GitHub API rate limit exceeded. Please retry later.";
    public static final String REST_CLIENT_DATA = "Data from Rest client: {}";
    public static final String HTTP_CLIENT_DATA = "Data from Http client: {}";
    public static final String RATE_LIMITER_PERMIT_ACQUIRED = "Rate limiter permit acquired [instance: {}, implementation: {}]";
    public static final String RATE_LIMITER_LIMIT_EXCEEDED = "Rate limiter rejected request [instance: {}, implementation: {}]";
    public static final String FEIGN_CLIENT_DATA = "Data from Feign client: {}";

    // prevent instantion
    private Messages(){}
}
