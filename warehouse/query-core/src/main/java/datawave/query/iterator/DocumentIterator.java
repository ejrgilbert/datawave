package datawave.query.iterator;

import java.io.IOException;

import datawave.query.attributes.Document;

import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;

/**
 * An iterator that can return documents
 */
public interface DocumentIterator extends SortedKeyValueIterator<Key,Value> {
    // get the document
    Document document();
    
    // move forward
    void move(Key pointer) throws IOException;
}
