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
import org.example.nikkediscordbot.entity.LastNotice;
import org.example.nikkediscordbot.repository.BotSettingRepository;
import org.example.nikkediscordbot.repository.LastNoticeRepository;
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
    private final LastNoticeRepository lastNoticeRepository;
    private final ObjectMapper mapper = new ObjectMapper(); // 추가
    private JDA jda;

    @Value("${discord.bot.token}")
    private String botToken;

    // 추가
    private static final int BOARD_NOTICE = 56;
    private static final int BOARD_UPDATE = 48;

    public NikkeBotService(BotSettingRepository botSettingRepository,
                           LastNoticeRepository lastNoticeRepository) {
        this.botSettingRepository = botSettingRepository;
        this.lastNoticeRepository = lastNoticeRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startBot() throws InterruptedException {
        jda = JDABuilder.createDefault(botToken)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(this)
                .build();
        jda.awaitReady();
        System.out.println("🤖 니케 MySQL 멀티서버 알림 봇이 구동되었습니다!");

        // 두 게시판 초기화
        for (int boardId : new int[]{BOARD_NOTICE, BOARD_UPDATE}) {
            try {
                JsonNode feedNode = fetchFeedNode(boardId);
                String latestLink = "https://game.naver.com/lounge/nikke/board/detail/"
                        + feedNode.path("feedId").asText("");

                lastNoticeRepository.findByBoardId(boardId).ifPresentOrElse(
                        saved -> System.out.println("✅ DB 복원 [" + boardId + "]: " + saved.getUrl()),
                        () -> {
                            lastNoticeRepository.save(new LastNotice(boardId, latestLink));
                            System.out.println("✅ 초기 저장 [" + boardId + "]: " + latestLink);
                        }
                );
            } catch (Exception e) {
                System.out.println("⚠️ 초기화 실패 [" + boardId + "]: " + e.getMessage());
            }
        }

        jda.updateCommands().addCommands(
                Commands.slash("공지채널설정", "니케 공지사항을 받을 채널을 지정합니다.")
                        .addOption(OptionType.CHANNEL, "채널", "공지를 받을 텍스트 채널을 선택하세요.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)),
                Commands.slash("공지사항", "가장 최근 니케 공지사항을 확인합니다."), // 추가
                Commands.slash("업데이트", "가장 최근 니케 업데이트 공지를 확인합니다.")
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("공지채널설정")) {
            if (event.getGuild() == null) {
                event.reply("서버 내에서만 사용할 수 있는 명령어입니다.").setEphemeral(true).queue();
                return;
            }
            String guildId = event.getGuild().getId();
            String channelId = event.getOption("채널").getAsChannel().getId();
            botSettingRepository.save(new BotSetting(guildId, channelId));
            event.reply("✅ 지정하신 채널로 니케 공지사항을 안전하게 배달해 드릴게요! 🫡")
                    .setEphemeral(true).queue();
        }

        // /공지사항 - boardId 56
        if (event.getName().equals("공지사항")) {
            event.deferReply().queue();
            try {
                JsonNode feedNode = fetchFeedNode(BOARD_NOTICE);
                event.getHook().sendMessageEmbeds(buildEmbed(feedNode, BOARD_NOTICE).build()).queue();
            } catch (Exception e) {
                event.getHook().sendMessage("❌ 에러: " + e.getMessage()).queue();
            }
        }

        // /업데이트 - boardId 48 (기존과 동일한 구조, boardId만 변경)
        if (event.getName().equals("업데이트")) {
            event.deferReply().queue();
            try {
                JsonNode feedNode = fetchFeedNode(BOARD_UPDATE);
                event.getHook().sendMessageEmbeds(buildEmbed(feedNode, BOARD_UPDATE).build()).queue();
            } catch (Exception e) {
                event.getHook().sendMessage("❌ 에러: " + e.getMessage()).queue();
            }
        }
    }

    @Scheduled(fixedDelay = 120000)
    public void checkNewNotice() {
        if (jda == null) return;
        checkBoard(BOARD_NOTICE); // 추가
        checkBoard(BOARD_UPDATE);
    }

    // 기존 로직 그대로, boardId 파라미터만 추가
    private void checkBoard(int boardId) {
        try {
            JsonNode feedNode = fetchFeedNode(boardId);
            String feedId = feedNode.path("feedId").asText("");
            String link = "https://game.naver.com/lounge/nikke/board/detail/" + feedId;

            String lastLink = lastNoticeRepository.findByBoardId(boardId)
                    .map(LastNotice::getUrl)
                    .orElse("");

            if (link.equals(lastLink)) {
                System.out.println("🔄 새 공지 없음 [boardId=" + boardId + "]");
                return;
            }

            // DB 업데이트
            LastNotice record = lastNoticeRepository.findByBoardId(boardId)
                    .orElse(new LastNotice(boardId, link));
            record.setUrl(link);
            lastNoticeRepository.save(record);

            // 임베드 전송
            net.dv8tion.jda.api.EmbedBuilder embed = buildEmbed(feedNode, boardId);
            List<BotSetting> settings = botSettingRepository.findAll();
            for (BotSetting setting : settings) {
                try {
                    net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel =
                            jda.getTextChannelById(setting.getChannelId());
                    if (channel == null) continue;
                    channel.sendMessageEmbeds(embed.build()).queue();
                } catch (Exception e) {
                    System.out.println("채널 전송 실패: " + setting.getChannelId());
                }
            }
            System.out.println("✅ 새 공지 전송 완료 [boardId=" + boardId + "]: "
                    + feedNode.path("title").asText());

        } catch (Exception e) {
            System.out.println("❌ 스케줄러 에러 [boardId=" + boardId + "]: " + e.getMessage());
        }
    }

    // ── 공통 유틸 (중복 제거용) ──────────────────────
    private JsonNode fetchFeedNode(int boardId) throws Exception {
        String apiUrl = "https://comm-api.game.naver.com/nng_main/v1/community/lounge/nikke/feed"
                + "?boardId=" + boardId + "&buffFilteringYN=N&limit=1&offset=0&order=NEW";

        String jsonBody = Jsoup.connect(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "ko-KR,ko;q=0.9")
                .header("Referer", "https://game.naver.com/lounge/nikke/board/" + boardId)
                .header("Origin", "https://game.naver.com")
                .ignoreContentType(true)
                .timeout(10000)
                .execute()
                .body();

        return mapper.readTree(jsonBody)
                .path("content").path("feeds").get(0).path("feed");
    }

    private net.dv8tion.jda.api.EmbedBuilder buildEmbed(JsonNode feedNode, int boardId) throws Exception {
        String title = org.jsoup.parser.Parser.unescapeEntities(
                feedNode.path("title").asText("제목 없음"), false);
        String feedId = feedNode.path("feedId").asText("");
        String link = "https://game.naver.com/lounge/nikke/board/detail/" + feedId;
        String imageUrl = feedNode.path("repImageUrl").asText("");
        String body = parseBody(feedNode, link);

        String emoji = boardId == BOARD_UPDATE ? "🔧" : "📢";
        int color  = boardId == BOARD_UPDATE ? 0xFF6B00 : 0x00AAFF;

        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
                .setTitle(emoji + " " + title, link)
                .setDescription(body)
                .setColor(color);

        if (!imageUrl.isEmpty()) embed.setImage(imageUrl);
        return embed;
    }

    private String parseBody(JsonNode feedNode, String link) throws Exception {
        String contentsRaw = feedNode.path("contents").asText("");
        StringBuilder bodyText = new StringBuilder();

        if (!contentsRaw.isEmpty()) {
            JsonNode components = mapper.readTree(contentsRaw)
                    .path("document").path("components");
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
        return body;
    }
}