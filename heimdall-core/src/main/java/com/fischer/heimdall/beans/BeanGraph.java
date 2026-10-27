package com.fischer.heimdall.beans;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;
import java.util.Set;

@Getter
@AllArgsConstructor
public class BeanGraph {
    private List<BeanNode> nodes;
    private Set<BeanEdge> edges;
}