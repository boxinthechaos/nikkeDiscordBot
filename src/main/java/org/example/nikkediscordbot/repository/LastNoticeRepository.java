package org.example.nikkediscordbot.repository;

import org.example.nikkediscordbot.entity.LastNotice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LastNoticeRepository extends JpaRepository<LastNotice, String> {
}
