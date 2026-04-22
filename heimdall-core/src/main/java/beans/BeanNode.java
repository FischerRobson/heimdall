package beans;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class BeanNode {
    private String id;
    private String fullClass;
    private String scope;
    private List<String> types;
}