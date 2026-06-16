package EngTeacher.service;

import EngTeacher.dto.agent.tools.ExerciseAttempt;
import EngTeacher.model.Exercise;
import EngTeacher.model.User;
import EngTeacher.model.UserSettings;
import EngTeacher.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final UserService userService;

    public void markCorrect(List<ExerciseAttempt.Correct> corrects) {
        if (corrects.isEmpty())  return;
        User user = userService.getUser(AuthUtils.currentUserId());
        corrects.forEach(correct -> {
            findExercise(user, correct.exerciseId()).ifPresent(exercise -> {
                exercise.setDone(true);
                updatePhrase(user, exercise.getPhrase().getId(), 10, 1);
            });
        });
        userService.save(user);
    }

    public void markIncorrect(List<ExerciseAttempt.Incorrect> incorrects) {
        if (incorrects.isEmpty())  return;
        User user = userService.getUser(AuthUtils.currentUserId());
        incorrects.forEach(incorrect -> {
            findExercise(user, incorrect.exerciseId()).ifPresent(exercise -> {
                exercise.setQuestion(incorrect.newQuestion());
                updatePhrase(user, exercise.getPhrase().getId(), -10, 0);
            });
        });
        userService.save(user);
    }

    private Optional<Exercise> findExercise(User user, String exerciseId) {
        return user.getSessions().stream()
                .flatMap(session -> session.getExercises().stream())
                .filter(exercise -> exercise.getId().equals(exerciseId))
                .findFirst();
    }

    private void updatePhrase(User user, String phraseId, int understandingRateDelta, int completedExercisesDelta) {
        final UserSettings settings = user.getSettings();
        user.getPhrases().stream()
                .filter(p -> p.getId().equals(phraseId))
                .findFirst()
                .ifPresent(phrase -> {
                    phrase.setUnderstandingRate(Math.clamp(phrase.getUnderstandingRate() + understandingRateDelta, settings.getMinUnderstandingRate(), settings.getMaxUnderstandingRate()));
                    phrase.setCompletedExercises(phrase.getCompletedExercises() + completedExercisesDelta);
                });
    }
}
