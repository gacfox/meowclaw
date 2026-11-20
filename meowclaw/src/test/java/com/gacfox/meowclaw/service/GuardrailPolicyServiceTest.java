package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.GuardrailPolicyConverter;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.GuardrailPolicy;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.GuardrailPolicyRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuardrailPolicyServiceTest {
    @Test
    void nestedSubagentInheritsRootConversationPolicy() {
        GuardrailPolicyRepository policyRepository = mock(GuardrailPolicyRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        GuardrailPolicyService service = new GuardrailPolicyService(policyRepository, conversationRepository,
                mock(GuardrailPolicyConverter.class), mock(GuardrailPolicyEngine.class));
        Conversation root = conversation(1L, null, 9L);
        Conversation child = conversation(2L, 1L, null);
        Conversation grandchild = conversation(3L, 2L, null);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(root));
        when(conversationRepository.findById(2L)).thenReturn(Optional.of(child));
        when(conversationRepository.findById(3L)).thenReturn(Optional.of(grandchild));
        GuardrailPolicy expected = new GuardrailPolicy();
        expected.setId(9L);
        when(policyRepository.findById(9L)).thenReturn(Optional.of(expected));

        GuardrailPolicy resolved = service.resolvePolicy(3L);

        assertThat(resolved).isSameAs(expected);
    }

    private Conversation conversation(Long id, Long parentId, Long policyId) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setParentConversationId(parentId);
        conversation.setGuardrailPolicyId(policyId);
        return conversation;
    }
}
