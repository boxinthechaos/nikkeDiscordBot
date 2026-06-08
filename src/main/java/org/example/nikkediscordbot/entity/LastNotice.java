package org.example.nikkediscordbot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "last_notice")
public class LastNotice {
    @Id
    private String url;

    public LastNotice() {}

    public LastNotice(String url) {
        this.url = url;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
