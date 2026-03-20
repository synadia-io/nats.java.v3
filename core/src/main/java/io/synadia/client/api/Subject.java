package io.synadia.client.api;

import io.nats.json.JsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static io.nats.json.JsonValueUtils.getLong;

/**
 * An object representing a stream's subject and the count of it's messages
 */
@NullMarked
public class Subject implements Comparable<Subject> {
    private final String name;
    private final long count;

    @Nullable
    static List<Subject> optionalListOf(@Nullable JsonValue vSubjects) {
        List<Subject> list = new ArrayList<>();
        if (vSubjects != null && vSubjects.map != null) {
            for (String subject : vSubjects.map.keySet()) {
                Long count = getLong(vSubjects.map.get(subject));
                if (count != null) {
                    list.add(new Subject(subject, count));
                }
            }
        }
        return list.size() == 0 ? null : list;
    }

    /**
     * Construct a Subject instance
     * @param name the subject name
     * @param count the message count
     */
    public Subject(String name, long count) {
        this.name = name;
        this.count = count;
    }

    /**
     * Get the subject name
     * @return the subject
     */
    public String getName() {
        return name;
    }

    /**
     * Get the subject message count
     * @return the count
     */
    public long getCount() {
        return count;
    }

    @Override
    public String toString() {
        return "Subject{" +
            "name='" + name + '\'' +
            ", count=" + count +
            '}';
    }

    @Override
    public int compareTo(Subject o) {
        return name.compareTo(o.name);
    }
}
