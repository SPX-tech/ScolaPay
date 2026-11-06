package com.scolapay.core.shared;

/** Pagination bornée : aucune liste ne peut charger une table entière en mémoire. */
public record PageQuery(int limit, int offset) {

    private static final int MAX_LIMIT = 200;
    private static final int DEFAULT_LIMIT = 50;

    public static PageQuery of(Integer limit, Integer offset) {
        int resolvedLimit = limit == null ? DEFAULT_LIMIT : limit;
        int resolvedOffset = offset == null ? 0 : offset;
        if (resolvedLimit < 1 || resolvedLimit > MAX_LIMIT || resolvedOffset < 0) {
            throw ApiException.invalid(
                    "INVALID_PAGE", "limit doit être entre 1 et " + MAX_LIMIT + ", offset ne peut pas être négatif.");
        }
        return new PageQuery(resolvedLimit, resolvedOffset);
    }
}
