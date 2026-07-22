package request.dto.server;

import lombok.Data;
import lombok.Builder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@Data
@Builder
@JacksonXmlRootElement(localName = "response")
public class ResponseDTO<T> {
    private final boolean success = true;
    private T data;
}