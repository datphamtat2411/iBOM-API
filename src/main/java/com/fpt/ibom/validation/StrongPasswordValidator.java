package com.fpt.ibom.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

	@Override
	public boolean isValid(String password, ConstraintValidatorContext context) {
		if (password == null || password.length() < 8 || password.length() > 72) {
			return false;
		}

		boolean uppercase = false;
		boolean lowercase = false;
		boolean digit = false;
		boolean special = false;
		for (int index = 0; index < password.length(); index++) {
			char character = password.charAt(index);
			uppercase |= Character.isUpperCase(character);
			lowercase |= Character.isLowerCase(character);
			digit |= Character.isDigit(character);
			special |= !Character.isLetterOrDigit(character) && !Character.isWhitespace(character);
		}
		return uppercase && lowercase && digit && special;
	}
}
