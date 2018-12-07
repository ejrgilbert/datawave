package datawave.query.function;

public interface ConfiguredFunction<A,B> extends com.google.common.base.Function<A,B> {
    void configure(java.util.Map<String,String> options);
}
