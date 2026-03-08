package io.synadia.client.api;

import io.synadia.client.support.JsonValue;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.support.JsonValueUtils.getLong;

/**
 * An object representing a stream's subject and the count of it's messages
 */
public class Subject implements Comparable<Subject> {
    private final String name;
    private final long count;

    static List<Subject> listOf(JsonValue vSubjects) {
        List<Subject> list = new ArrayList<>();
        if (vSubjects != null && vSubjects.map != null) {
            for (String subject : vSubjects.map.keySet()) {
                Long count = getLong(vSubjects.map.get(subject));
                if (count != null) {
                    list.add(new Subject(subject, count));
                }
            }
        }
        return list;
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
    @NonNull
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
