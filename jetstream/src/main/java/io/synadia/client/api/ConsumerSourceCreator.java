package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.utils.ApiConstants.DELIVER_SUBJECT;
import static io.synadia.client.utils.ApiConstants.NAME;
import static io.synadia.client.utils.JsValidator.validateConsumerName;
import static io.synadia.client.utils.Validator.validateSubject;

/**
 * ConsumerSourceCreator is used to create the consumer information for durable sourcing.
 */
@NullMarked
public class ConsumerSourceCreator implements JsonSerializable {
    private String name;
    private String deliverSubject;

    /**
     * Construct a ConsumerSourceCreator
     * @param name the durable consumer name
     * @param deliverSubject the deliver subject
     */
    public ConsumerSourceCreator(String name, String deliverSubject) {
        this.name = validateConsumerName(name, true);
        this.deliverSubject = validateSubject(deliverSubject, true);
    }

    /**
     * Construct a ConsumerSourceCreator from a ConsumerSource (server response)
     * @param cs the consumer source to copy from
     */
    ConsumerSourceCreator(ConsumerSource cs) {
        this(cs.getName(), cs.getDeliverSubject());
    }

    /**
     * Set the consumer name.
     * @param name the consumer name
     * @return this instance for chaining
     */
    public ConsumerSourceCreator name(String name) {
        this.name = validateConsumerName(name, true);
        return this;
    }

    /**
     * Set the deliver subject.
     * @param deliverSubject the deliver subject
     * @return this instance for chaining
     */
    public ConsumerSourceCreator deliverSubject(String deliverSubject) {
        this.deliverSubject = validateSubject(deliverSubject, true);
        return this;
    }

    /**
     * The durable consumer name used for sourcing.
     * @return the consumer name
     */
    public String getName() {
        return name;
    }

    /**
     * The subject to deliver messages to.
     * @return the deliver subject
     */
    public String getDeliverSubject() {
        return deliverSubject;
    }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, NAME, name);
        addField(sb, DELIVER_SUBJECT, deliverSubject);
        return endJson(sb).toString();
    }

    @Override
    public String toString() {
        return "ConsumerSourceCreator" + toJson();
    }

    @Override
    public final boolean equals(@Nullable Object o) {
        if (!(o instanceof ConsumerSourceCreator that)) return false;
        return Objects.equals(name, that.name)
            && Objects.equals(deliverSubject, that.deliverSubject);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(deliverSubject);
        return result;
    }
}
