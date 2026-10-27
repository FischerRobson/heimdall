package beans;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class BeanNode {
    // fully qualified class name — unique, unlike the simple name (two "OrderService"
    // classes in different packages used to collapse into a single node)
    private String id;
    // simple class name, for display
    private String name;
    private String fullClass;
    private String scope;
    private List<String> types;
}