package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;

/**
 * MirrorCreator is used to create a Mirror configuration for use in a StreamCreator.
 */
@NullMarked
public class MirrorCreator extends StreamSourceCreator<MirrorCreator> {

    /**
     * Construct a MirrorCreator
     * @param sourceStreamName the source stream name
     */
    public MirrorCreator(String sourceStreamName) {
        super(sourceStreamName);
    }

    /**
     * Construct a MirrorCreator by copying another
     * @param newName the new name to replace the basis name
     * @param basis the source base the copy on for all the other fields
     */
    public MirrorCreator(String newName, MirrorCreator basis) {
        super(newName, basis);
    }

    /**
     * Construct a MirrorCreator from a Mirror (server response)
     * @param m the mirror to copy from
     */
    MirrorCreator(Mirror m) {
        super(m);
    }

    @Override
    public String toString() {
        return "MirrorCreator " + toJson();
    }
}
