package de.unihamburg.daibetes.service;

import lombok.Data;

@Data
public class UMLSResultWrapper<T> {
    private Integer pageSize;
    private Integer pageNumber;
    private Integer pageCount;
    private T result;
}
