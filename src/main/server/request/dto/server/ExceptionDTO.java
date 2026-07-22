package request.dto.server;

import lombok.Data;
import lombok.Builder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@Data
@Builder
@JacksonXmlRootElement(localName = "error")
public class ExceptionDTO<T> {
    private final boolean success = false;
    private T data;
}
