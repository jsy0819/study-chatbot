package com.studychatbot.backend.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class QuestionCountValidator implements ConstraintValidator<ValidQuestionCount, Integer> {

    private static final Set<Integer> ALLOWED = Set.of(3, 5, 10);

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        // null은 @NotNull이 별도로 잡으므로 여기선 통과
        return value == null || ALLOWED.contains(value);
    }
}
