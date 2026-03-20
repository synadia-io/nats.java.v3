package io.synadia.client.support;

import io.synadia.client.ConsumeOptions;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;

import static io.synadia.client.ConsumeOptions.DEFAULT_CONSUME_OPTIONS;

public class SerializableConsumeOptions implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private transient ConsumeOptions co;

    public SerializableConsumeOptions() {
        setConsumeOptions(DEFAULT_CONSUME_OPTIONS);
    }

    public SerializableConsumeOptions(ConsumeOptions co) {
        setConsumeOptions(co);
    }

    public SerializableConsumeOptions(ConsumeOptions.Builder builder) {
        setConsumeOptions(builder.build());
    }

    public void setConsumeOptions(ConsumeOptions co) {
        this.co = co;
    }

    public ConsumeOptions getConsumeOptions() {
        return co;
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(co.toJson());
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        co = ConsumeOptions.builder().json(in.readUTF()).build();
    }
}
