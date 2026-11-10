package com.scolapay.core.identity.internal;

import com.scolapay.core.identity.InvitationDelivery;
import com.scolapay.core.identity.Identifier;
import com.scolapay.core.shared.GoLiveCondition;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class InvitationDeliveryCondition implements GoLiveCondition {

    private final ObjectProvider<InvitationDelivery> delivery;

    InvitationDeliveryCondition(ObjectProvider<InvitationDelivery> delivery) {
        this.delivery = delivery;
    }

    @Override
    public String name() {
        return "canal-de-remise-des-invitations";
    }

    @Override
    public Optional<String> unmet() {
        InvitationDelivery channel = delivery.getIfAvailable();
        return channel == null || !channel.supports(Identifier.Kind.EMAIL) || !channel.supports(Identifier.Kind.PHONE)
                ? Optional.of("les canaux e-mail et téléphone des invitations doivent être configurés")
                : Optional.empty();
    }
}
