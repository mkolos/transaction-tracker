package com.kolosh.transactiontracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Mockito generates subclasses at runtime via ByteBuddy. Older ByteBuddy versions reject
 * Java 25 class files, so this fails loudly if the managed versions regress.
 */
class MockitoJava25Test {

    @Test
    @SuppressWarnings("unchecked")
    void mockitoCanMockOnJava25() {
        List<String> list = mock(List.class);
        when(list.get(0)).thenReturn("hello");

        assertThat(list.get(0)).isEqualTo("hello");
        verify(list).get(0);
    }
}
