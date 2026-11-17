package com.studypilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studypilot.entity.Conversation;
import com.studypilot.entity.Document;
import com.studypilot.entity.KnowledgeBase;
import com.studypilot.entity.Quiz;
import com.studypilot.entity.QuizQuestion;
import com.studypilot.entity.StudyPlan;
import com.studypilot.entity.StudyTask;
import com.studypilot.mapper.ConversationMapper;
import com.studypilot.mapper.DocumentMapper;
import com.studypilot.mapper.KnowledgeBaseMapper;
import com.studypilot.mapper.QuizMapper;
import com.studypilot.mapper.QuizQuestionMapper;
import com.studypilot.mapper.StudyPlanMapper;
import com.studypilot.mapper.StudyTaskMapper;
import com.studypilot.security.UserContext;
import com.studypilot.vo.DashboardOverviewVO;
import com.studypilot.vo.DocumentStatisticsVO;
import com.studypilot.vo.LearningStatisticsVO;
import com.studypilot.vo.QuizStatisticsVO;
import com.studypilot.vo.StatItemVO;
import com.studypilot.vo.TrendItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台真实统计。所有数字均来自当前用户（UserContext）的 MySQL 数据，
 * MyBatis-Plus @TableLogic 自动过滤 deleted=0；无记录返回 0 / 空列表，绝不虚构。
 * 纯只读聚合，不新增表、不写库。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ConversationMapper conversationMapper;
    private final StudyPlanMapper studyPlanMapper;
    private final StudyTaskMapper studyTaskMapper;
    private final QuizMapper quizMapper;
    private final QuizQuestionMapper quizQuestionMapper;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final int DAYS = 7;

    private Long uid() {
        return UserContext.getUserId();
    }

    private long cnt(Long c) {
        return c == null ? 0L : c;
    }

    public DashboardOverviewVO overview() {
        Long uid = uid();
        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setDocumentTotal(cnt(documentMapper.selectCount(
                new LambdaQueryWrapper<Document>().eq(Document::getUserId, uid))));
        vo.setKnowledgeBaseTotal(cnt(knowledgeBaseMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getUserId, uid))));
        vo.setParsedDocumentTotal(cnt(documentMapper.selectCount(
                new LambdaQueryWrapper<Document>().eq(Document::getUserId, uid).eq(Document::getStatus, 4))));
        vo.setVectorizedDocumentTotal(cnt(documentMapper.selectCount(
                new LambdaQueryWrapper<Document>().eq(Document::getUserId, uid).eq(Document::getVectorStatus, 2))));
        vo.setConversationTotal(cnt(conversationMapper.selectCount(
                new LambdaQueryWrapper<Conversation>().eq(Conversation::getUserId, uid))));
        vo.setStudyPlanTotal(cnt(studyPlanMapper.selectCount(
                new LambdaQueryWrapper<StudyPlan>().eq(StudyPlan::getUserId, uid))));
        vo.setCompletedTaskTotal(cnt(studyTaskMapper.selectCount(
                new LambdaQueryWrapper<StudyTask>().eq(StudyTask::getUserId, uid).eq(StudyTask::getStatus, 1))));
        vo.setTotalTaskCount(cnt(studyTaskMapper.selectCount(
                new LambdaQueryWrapper<StudyTask>().eq(StudyTask::getUserId, uid))));
        vo.setQuizPracticeTotal(cnt(quizMapper.selectCount(
                new LambdaQueryWrapper<Quiz>().eq(Quiz::getUserId, uid).eq(Quiz::getStatus, 1))));
        List<StudyPlan> plans = studyPlanMapper.selectList(
                new LambdaQueryWrapper<StudyPlan>().eq(StudyPlan::getUserId, uid));
        vo.setLearningProgress(avgProgress(plans));
        return vo;
    }

    public DocumentStatisticsVO documentStatistics() {
        Long uid = uid();
        List<Document> docs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>().eq(Document::getUserId, uid));
        DocumentStatisticsVO vo = new DocumentStatisticsVO();
        vo.setTotal(docs.size());
        vo.setParsed(docs.stream().filter(d -> eq(d.getStatus(), 4)).count());
        vo.setVectorized(docs.stream().filter(d -> eq(d.getVectorStatus(), 2)).count());
        vo.setParseFailed(docs.stream().filter(d -> eq(d.getStatus(), 5)).count());
        vo.setPending(docs.stream().filter(d -> d.getStatus() != null && d.getStatus() < 4).count());
        vo.setParseStatusDistribution(parseDist(docs));
        vo.setVectorStatusDistribution(vectorDist(docs));
        vo.setUploadTrend(trend7(docs.stream().map(Document::getCreateTime).toList()));
        return vo;
    }

    public LearningStatisticsVO learningStatistics() {
        Long uid = uid();
        List<StudyPlan> plans = studyPlanMapper.selectList(
                new LambdaQueryWrapper<StudyPlan>().eq(StudyPlan::getUserId, uid));
        List<StudyTask> tasks = studyTaskMapper.selectList(
                new LambdaQueryWrapper<StudyTask>().eq(StudyTask::getUserId, uid));
        LearningStatisticsVO vo = new LearningStatisticsVO();
        vo.setPlanTotal(plans.size());
        vo.setTaskTotal(tasks.size());
        vo.setCompletedTaskTotal(tasks.stream().filter(t -> eq(t.getStatus(), 1)).count());
        vo.setAvgProgress(avgProgress(plans));

        Map<Long, long[]> byPlan = new HashMap<>();
        for (StudyTask t : tasks) {
            long[] arr = byPlan.computeIfAbsent(t.getPlanId(), k -> new long[2]);
            arr[0]++;
            if (eq(t.getStatus(), 1)) {
                arr[1]++;
            }
        }
        List<LearningStatisticsVO.PlanProgress> pp = new ArrayList<>();
        for (StudyPlan p : plans) {
            long[] arr = byPlan.getOrDefault(p.getId(), new long[2]);
            LearningStatisticsVO.PlanProgress x = new LearningStatisticsVO.PlanProgress();
            x.setPlanId(p.getId());
            x.setTitle(p.getTitle());
            x.setProgress(p.getProgress() == null ? 0 : p.getProgress());
            x.setTotal(arr[0]);
            x.setCompleted(arr[1]);
            pp.add(x);
        }
        vo.setPlanProgressList(pp);
        vo.setCompletionTrend(trend7(tasks.stream()
                .filter(t -> eq(t.getStatus(), 1)).map(StudyTask::getUpdateTime).toList()));
        vo.setActivityTrend(buildActivity(uid));
        return vo;
    }

    public QuizStatisticsVO quizStatistics() {
        Long uid = uid();
        List<Quiz> quizzes = quizMapper.selectList(
                new LambdaQueryWrapper<Quiz>().eq(Quiz::getUserId, uid));
        List<Quiz> submitted = quizzes.stream().filter(q -> eq(q.getStatus(), 1)).toList();
        QuizStatisticsVO vo = new QuizStatisticsVO();
        vo.setQuizTotal(quizzes.size());
        vo.setPracticedTotal(submitted.size());
        vo.setAvgScore(submitted.isEmpty() ? 0 : (int) Math.round(submitted.stream()
                .mapToInt(q -> q.getScore() == null ? 0 : q.getScore()).average().orElse(0)));
        vo.setScoreDistribution(scoreDist(submitted));

        List<Long> quizIds = quizzes.stream().map(Quiz::getId).toList();
        List<QuizQuestion> questions = quizIds.isEmpty() ? List.of()
                : quizQuestionMapper.selectList(new LambdaQueryWrapper<QuizQuestion>().in(QuizQuestion::getQuizId, quizIds));
        vo.setQuestionTypeDistribution(typeDist(questions));
        vo.setPracticeTrend(trend7(submitted.stream().map(Quiz::getUpdateTime).toList()));
        vo.setRecentScores(submitted.stream()
                .sorted(Comparator.comparing(Quiz::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(q -> {
                    QuizStatisticsVO.RecentScore r = new QuizStatisticsVO.RecentScore();
                    r.setQuizId(q.getId());
                    r.setTitle(q.getTitle());
                    r.setScore(q.getScore() == null ? 0 : q.getScore());
                    r.setCreateTime(q.getUpdateTime() == null ? "" : q.getUpdateTime().format(STAMP));
                    return r;
                }).toList());
        return vo;
    }

    // ---------- helpers ----------

    private boolean eq(Integer a, int b) {
        return a != null && a == b;
    }

    private int avgProgress(List<StudyPlan> plans) {
        if (plans.isEmpty()) {
            return 0;
        }
        return (int) Math.round(plans.stream()
                .mapToInt(p -> p.getProgress() == null ? 0 : p.getProgress()).average().orElse(0));
    }

    private List<StatItemVO> parseDist(List<Document> docs) {
        String[] labels = {"待解析", "解析中", "切片中", "向量化中", "已解析", "解析失败"};
        long[] c = new long[6];
        for (Document d : docs) {
            int s = d.getStatus() == null ? 0 : d.getStatus();
            if (s >= 0 && s <= 5) {
                c[s]++;
            }
        }
        List<StatItemVO> out = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            out.add(new StatItemVO(labels[i], c[i]));
        }
        return out;
    }

    private List<StatItemVO> vectorDist(List<Document> docs) {
        String[] labels = {"未向量化", "向量化中", "已向量化", "向量化失败"};
        long[] c = new long[4];
        for (Document d : docs) {
            int s = d.getVectorStatus() == null ? 0 : d.getVectorStatus();
            if (s >= 0 && s <= 3) {
                c[s]++;
            }
        }
        List<StatItemVO> out = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            out.add(new StatItemVO(labels[i], c[i]));
        }
        return out;
    }

    private List<StatItemVO> scoreDist(List<Quiz> submitted) {
        String[] labels = {"0-59", "60-69", "70-79", "80-89", "90-100"};
        long[] c = new long[5];
        for (Quiz q : submitted) {
            int s = q.getScore() == null ? 0 : q.getScore();
            int idx = s < 60 ? 0 : s < 70 ? 1 : s < 80 ? 2 : s < 90 ? 3 : 4;
            c[idx]++;
        }
        List<StatItemVO> out = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            out.add(new StatItemVO(labels[i], c[i]));
        }
        return out;
    }

    private List<StatItemVO> typeDist(List<QuizQuestion> questions) {
        String[] labels = {"单选", "多选", "判断", "简答"};
        long[] c = new long[4];
        for (QuizQuestion q : questions) {
            int t = q.getType() == null ? 0 : q.getType();
            if (t >= 1 && t <= 4) {
                c[t - 1]++;
            }
        }
        List<StatItemVO> out = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            out.add(new StatItemVO(labels[i], c[i]));
        }
        return out;
    }

    private List<TrendItemVO> trend7(List<LocalDateTime> times) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(DAYS - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (LocalDateTime t : times) {
            if (t == null) {
                continue;
            }
            LocalDate d = t.toLocalDate();
            if (!d.isBefore(start) && !d.isAfter(today)) {
                counts.merge(d, 1L, Long::sum);
            }
        }
        List<TrendItemVO> out = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            LocalDate d = start.plusDays(i);
            out.add(new TrendItemVO(d.format(DAY), counts.getOrDefault(d, 0L)));
        }
        return out;
    }

    private LearningStatisticsVO.ActivityTrend buildActivity(Long uid) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(DAYS - 1L);
        LocalDateTime windowStart = start.atStartOfDay();
        long[] docA = new long[DAYS];
        long[] taskA = new long[DAYS];
        long[] quizA = new long[DAYS];
        long[] convA = new long[DAYS];
        for (Document d : documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, uid).ge(Document::getCreateTime, windowStart))) {
            bucket(docA, d.getCreateTime(), start);
        }
        for (StudyTask t : studyTaskMapper.selectList(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getUserId, uid).eq(StudyTask::getStatus, 1).ge(StudyTask::getUpdateTime, windowStart))) {
            bucket(taskA, t.getUpdateTime(), start);
        }
        for (Quiz q : quizMapper.selectList(new LambdaQueryWrapper<Quiz>()
                .eq(Quiz::getUserId, uid).eq(Quiz::getStatus, 1).ge(Quiz::getUpdateTime, windowStart))) {
            bucket(quizA, q.getUpdateTime(), start);
        }
        for (Conversation c : conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, uid).ge(Conversation::getCreateTime, windowStart))) {
            bucket(convA, c.getCreateTime(), start);
        }
        List<String> dates = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            dates.add(start.plusDays(i).format(DAY));
        }
        LearningStatisticsVO.ActivityTrend at = new LearningStatisticsVO.ActivityTrend();
        at.setDates(dates);
        at.setDocuments(toList(docA));
        at.setTasks(toList(taskA));
        at.setQuizzes(toList(quizA));
        at.setConversations(toList(convA));
        return at;
    }

    private void bucket(long[] arr, LocalDateTime t, LocalDate start) {
        if (t == null) {
            return;
        }
        int idx = (int) ChronoUnit.DAYS.between(start, t.toLocalDate());
        if (idx >= 0 && idx < DAYS) {
            arr[idx]++;
        }
    }

    private List<Long> toList(long[] a) {
        List<Long> l = new ArrayList<>(a.length);
        for (long v : a) {
            l.add(v);
        }
        return l;
    }
}