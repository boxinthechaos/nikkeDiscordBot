package org.example.nikkediscordbot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "last_notice")
public class LastNotice {
    @Id
    private int boardId;

    private String url;

    public LastNotice(int boardId, String url) {
        this.boardId = boardId;
        this.url = url;
    }

    public LastNotice() {

    }

    public int getBoardId() { return boardId; }
    public void setBoardId(int boardId) { this.boardId = boardId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
