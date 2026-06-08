package org.example.nikkediscordbot.repository;

import org.example.nikkediscordbot.entity.LastNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LastNoticeRepository extends JpaRepository<LastNotice, Integer> {
    Optional<LastNotice> findByBoardId(int boardId);
}
