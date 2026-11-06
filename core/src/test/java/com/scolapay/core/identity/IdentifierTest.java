package com.scolapay.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scolapay.core.shared.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifierTest {

    @Test
    void normaliseUnEmail() {
        assertThat(Identifier.parse("  Parent.Test@Ecole.TD ")).isEqualTo(new Identifier(Identifier.Kind.EMAIL, "parent.test@ecole.td"));
    }

    @Test
    void normaliseUnTelephone() {
        assertThat(Identifier.parse("+235 66 12-34.56")).isEqualTo(new Identifier(Identifier.Kind.PHONE, "+23566123456"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"sans-arobase", "a@b", "a@@b.td", "66123456", "+0123456789", "+23", "a b@ecole.td"})
    void refuseUnIdentifiantInvalide(String raw) {
        assertThatThrownBy(() -> Identifier.parse(raw)).isInstanceOf(ApiException.class);
    }
}
