package org.example.nikkediscordbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.example.nikkediscordbot.entity.BotSetting;
import org.example.nikkediscordbot.repository.BotSettingRepository;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@EnableScheduling
public class NikkeBotService extends ListenerAdapter {
    private final BotSettingRepository botSettingRepository;
    private JDA jda;

    public NikkeBotService(BotSettingRepository botSettingRepository) {
        this.botSettingRepository = botSettingRepository;
    }

    @Value("${discord.bot.token}")
    private String botToken;

    private String lastNoticeLink = "";

    // 네이버 라운지 게시판 기본 링크
    private final String NIKKE_LOUNGE_URL = "https://game.naver.com/lounge/nikke/board/56";

    // 💡 네이버가 실제로 글 목록 데이터를 가져오는 API 주소 (boardId 56 기준)
    private final String NAVER_API_URL = "https://comm-api.game.naver.com/nng_main/v1/lounge/nikke/board/56/list?limit=5";

    @EventListener(ApplicationReadyEvent.class)
    public void startBot() throws InterruptedException {
        jda = JDABuilder.createDefault(botToken)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(this)
                .build();

        jda.awaitReady();
        System.out.println("🤖 니케 MySQL 멀티서버 알림 봇이 구동되었습니다!");

        // ✅ 이 부분 추가 - 봇 시작 시 최신 글 ID를 미리 저장해서 중복 전송 방지
        try {
            String apiUrl = "https://comm-api.game.naver.com/nng_main/v1/community/lounge/nikke/feed"
                    + "?boardId=56&buffFilteringYN=N&limit=1&offset=0&order=NEW";
            String jsonBody = Jsoup.connect(apiUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .ignoreContentType(true)
                    .timeout(30000)
                    .execute()
                    .body();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode feedNode = mapper.readTree(jsonBody)
                    .path("content").path("feeds").get(0).path("feed");
            String feedId = feedNode.path("feedId").asText("");
            lastNoticeLink = "https://game.naver.com/lounge/nikke/board/detail/" + feedId;
            System.out.println("✅ 초기 공지 ID 저장 완료: " + lastNoticeLink);
        } catch (Exception e) {
            System.out.println("⚠️ 초기 공지 ID 저장 실패: " + e.getMessage());
        }

        jda.updateCommands().addCommands(
                Commands.slash("공지채널설정", "니케 공지사항을 받을 채널을 지정합니다.")
                        .addOption(OptionType.CHANNEL, "채널", "공지를 받을 텍스트 채널을 선택하세요.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)),
                Commands.slash("업데이트", "가장 최근 니케 업데이트 공지를 확인합니다.")
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("공지채널설정")) {
            // ... (기존 공지채널설정 코드는 그대로 둡니다) ...
            if (event.getGuild() == null) {
                event.reply("서버 내에서만 사용할 수 있는 명령어입니다.").setEphemeral(true).queue();
                return;
            }

            String guildId = event.getGuild().getId();
            String channelId = event.getOption("채널").getAsChannel().getId();

            BotSetting setting = new BotSetting(guildId, channelId);
            botSettingRepository.save(setting);

            event.reply("✅ 지정하신 채널로 니케 공지사항을 안전하게 배달해 드릴게요! 🫡")
                    .setEphemeral(true)
                    .queue();
        }

        // 💡 여기서부터 변경된 플랜 B 크롤링 코드입니다!
        if (event.getName().equals("업데이트")) {
            event.deferReply().queue();

            try {
                String apiUrl = "https://comm-api.game.naver.com/nng_main/v1/community/lounge/nikke/feed"
                        + "?boardId=56&buffFilteringYN=N&limit=1&offset=0&order=NEW";

                String jsonBody = Jsoup.connect(apiUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Accept-Language", "ko-KR,ko;q=0.9")
                        .header("Referer", "https://game.naver.com/lounge/nikke/board/56")
                        .header("Origin", "https://game.naver.com")
                        .ignoreContentType(true)
                        .timeout(10000)
                        .execute()
                        .body();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(jsonBody);
                JsonNode feedNode = root.path("content").path("feeds").get(0).path("feed");

                // 제목
                String title = org.jsoup.parser.Parser.unescapeEntities(
                        feedNode.path("title").asText("제목 없음"), false);

                // 링크
                String feedId = feedNode.path("feedId").asText("");
                String link = "https://game.naver.com/lounge/nikke/board/detail/" + feedId;

                // 이미지
                String imageUrl = feedNode.path("repImageUrl").asText("");

                // 내용 추출 (contents JSON 안의 텍스트 노드들을 이어붙임)
                String contentsRaw = feedNode.path("contents").asText("");
                StringBuilder bodyText = new StringBuilder();
                if (!contentsRaw.isEmpty()) {
                    JsonNode contentsJson = mapper.readTree(contentsRaw);
                    JsonNode components = contentsJson.path("document").path("components");
                    for (JsonNode component : components) {
                        JsonNode value = component.path("value");
                        if (value.isArray()) {
                            for (JsonNode paragraph : value) {
                                JsonNode nodes = paragraph.path("nodes");
                                if (nodes.isArray()) {
                                    for (JsonNode node : nodes) {
                                        String text = node.path("value").asText("").trim();
                                        if (!text.isEmpty()) {
                                            bodyText.append(text).append("\n");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 내용이 너무 길면 자르기 (Discord Embed 최대 4096자)
                String body = bodyText.toString().trim();
                if (body.length() > 800) {
                    body = body.substring(0, 800) + "...\n\n더 보기: " + link;
                } else if (body.isEmpty()) {
                    body = "내용 없음";
                }

                // Discord Embed 전송
                net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
                        .setTitle("📢 " + title, link)
                        .setDescription(body)
                        .setColor(0x00AAFF);

                if (!imageUrl.isEmpty()) {
                    embed.setImage(imageUrl);
                }

                event.getHook().sendMessageEmbeds(embed.build()).queue();

            } catch (org.jsoup.HttpStatusException se) {
                event.getHook().sendMessage("❌ HTTP " + se.getStatusCode() + " 에러").queue();
                se.printStackTrace();
            } catch (Exception e) {
                event.getHook().sendMessage("❌ 에러: " + e.getMessage()).queue();
                e.printStackTrace();
            }
        }
    }
    @Scheduled(fixedDelay = 120000) // 2분마다 실행
    public void checkNewNotice() {
        if (jda == null) return;
        try {
            String apiUrl = "https://comm-api.game.naver.com/nng_main/v1/community/lounge/nikke/feed"
                    + "?boardId=56&buffFilteringYN=N&limit=1&offset=0&order=NEW";

            String jsonBody = Jsoup.connect(apiUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .header("Referer", "https://game.naver.com/lounge/nikke/board/56")
                    .header("Origin", "https://game.naver.com")
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute()
                    .body();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonBody);
            JsonNode feedNode = root.path("content").path("feeds").get(0).path("feed");

            String feedId = feedNode.path("feedId").asText("");
            String link = "https://game.naver.com/lounge/nikke/board/detail/" + feedId;

            // 이전에 보낸 글과 같으면 무시
            if (link.equals(lastNoticeLink)) return;
            lastNoticeLink = link;

            String title = org.jsoup.parser.Parser.unescapeEntities(
                    feedNode.path("title").asText("제목 없음"), false);
            String imageUrl = feedNode.path("repImageUrl").asText("");

            // 내용 추출
            String contentsRaw = feedNode.path("contents").asText("");
            StringBuilder bodyText = new StringBuilder();
            if (!contentsRaw.isEmpty()) {
                JsonNode contentsJson = mapper.readTree(contentsRaw);
                JsonNode components = contentsJson.path("document").path("components");
                for (JsonNode component : components) {
                    JsonNode value = component.path("value");
                    if (value.isArray()) {
                        for (JsonNode paragraph : value) {
                            JsonNode nodes = paragraph.path("nodes");
                            if (nodes.isArray()) {
                                for (JsonNode node : nodes) {
                                    String text = node.path("value").asText("").trim();
                                    if (!text.isEmpty()) bodyText.append(text).append("\n");
                                }
                            }
                        }
                    }
                }
            }

            String body = bodyText.toString().trim();
            if (body.length() > 800) body = body.substring(0, 800) + "...\n\n더 보기: " + link;
            if (body.isEmpty()) body = "내용 없음";

            // DB에서 설정된 모든 서버 채널에 전송
            net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
                    .setTitle("📢 " + title, link)
                    .setDescription(body)
                    .setColor(0x00AAFF);
            if (!imageUrl.isEmpty()) embed.setImage(imageUrl);

            List<BotSetting> settings = botSettingRepository.findAll();
            for (BotSetting setting : settings) {
                try {
                    net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel =
                            jda.getTextChannelById(setting.getChannelId());
                    if (channel == null) {
                        System.out.println("채널 못 찾음: " + setting.getChannelId());
                        return;
                    }
                    channel.sendMessageEmbeds(embed.build()).queue();
                } catch (Exception e) {
                    System.out.println("채널 전송 실패: " + setting.getChannelId());
                }
            }

            System.out.println("✅ 새 공지 전송 완료: " + title);

        } catch (Exception e) {
            System.out.println("❌ 스케줄러 에러: " + e.getMessage());
        }
    }
}