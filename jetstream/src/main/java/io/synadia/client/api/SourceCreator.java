package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;

/**
 * SourceCreator is used to create a Source configuration for use in a StreamCreator.
 */
@NullMarked
public class SourceCreator extends StreamSourceCreator<SourceCreator> {

    /**
     * Construct a SourceCreator
     * @param name the source stream name
     */
    public SourceCreator(String name) {
        super(name);
    }

    /**
     * Construct a SourceCreator by copying another
     * @param newName the new name to replace the basis name
     * @param basis the source base the copy on for all the other fields
     */
    public SourceCreator(String newName, SourceCreator basis) {
        super(newName, basis);
    }

    /**
     * Construct a SourceCreator from a Source (server response)
     * @param s the source to copy from
     */
    SourceCreator(Source s) {
        super(s);
    }

    @Override
    public String toString() {
        return "SourceCreator " + toJson();
    }
}
