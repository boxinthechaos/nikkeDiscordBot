package org.example.nikkediscordbot.repository;

import org.example.nikkediscordbot.entity.BotSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BotSettingRepository extends JpaRepository<BotSetting, String> {
}
