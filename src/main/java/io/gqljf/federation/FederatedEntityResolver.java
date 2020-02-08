package io.gqljf.federation;

import java.lang.reflect.ParameterizedType;
import java.util.function.Function;

// `abstract` makes it possible to store generic types
public abstract class FederatedEntityResolver<ID, T> {

    private final String typeName;
    private final Class<ID> idClass;
    private final Class<T> entityClass;
    private final Function<ID, T> mapper;

    public FederatedEntityResolver(String typeName, Function<ID, T> mapper) {
        this.typeName = typeName;
        this.mapper = mapper;
        this.idClass = getGenericTypeClass(0);
        this.entityClass = getGenericTypeClass(1);
    }

    public T getEntity(ID id) {
        return mapper.apply(id);
    }

    public String getTypeName() {
        return typeName;
    }

    public Class<ID> getIdClass() {
        return idClass;
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    @SuppressWarnings("unchecked")
    private <C> Class<C> getGenericTypeClass(int i) {
        return ((Class<C>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[i]);
    }
}
