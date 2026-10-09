package com.talleres360.orders.validation;

public final class RutValidator {
	private RutValidator() {}
	public static boolean isValid(String rut) {
		if (rut == null) return false;
		String normalized = rut.trim().toUpperCase(java.util.Locale.ROOT);
		if (!normalized.matches("(?:\\d{7,8}|\\d{1,2}\\.\\d{3}\\.\\d{3})-?[0-9K]")) return false;
		String clean = normalized.replace(".", "").replace("-", "");
		if (!clean.matches("\\d{7,8}[0-9K]")) return false;
		String body = clean.substring(0, clean.length() - 1);
		if (Long.parseLong(body) == 0) return false;
		char supplied = clean.charAt(clean.length() - 1);
		int sum = 0, multiplier = 2;
		for (int index = body.length() - 1; index >= 0; index--) {
			sum += Character.getNumericValue(body.charAt(index)) * multiplier;
			multiplier = multiplier == 7 ? 2 : multiplier + 1;
		}
		int result = 11 - (sum % 11);
		char expected = result == 11 ? '0' : result == 10 ? 'K' : Character.forDigit(result, 10);
		return supplied == expected;
	}
}
