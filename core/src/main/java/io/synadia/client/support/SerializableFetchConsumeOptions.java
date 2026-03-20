package io.synadia.client.support;

import io.synadia.client.FetchConsumeOptions;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;

import static io.synadia.client.FetchConsumeOptions.DEFAULT_FETCH_OPTIONS;

public class SerializableFetchConsumeOptions implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private transient FetchConsumeOptions fo;

    public SerializableFetchConsumeOptions() {
        setFetchConsumeOptions(DEFAULT_FETCH_OPTIONS);
    }

    public SerializableFetchConsumeOptions(FetchConsumeOptions fo) {
        setFetchConsumeOptions(fo);
    }

    public SerializableFetchConsumeOptions(FetchConsumeOptions.Builder builder) {
        setFetchConsumeOptions(builder.build());
    }

    public void setFetchConsumeOptions(FetchConsumeOptions fo) {
        this.fo = fo;
    }

    public FetchConsumeOptions getFetchConsumeOptions() {
        return fo;
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(fo.toJson());
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        fo = FetchConsumeOptions.builder().json(in.readUTF()).build();
    }
}
