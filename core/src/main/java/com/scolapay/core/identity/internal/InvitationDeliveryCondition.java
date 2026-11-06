package com.scolapay.core.identity.internal;

import com.scolapay.core.identity.InvitationDelivery;
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
        return delivery.getIfAvailable() == null
                ? Optional.of("aucun canal de remise des invitations n'est configuré")
                : Optional.empty();
    }
}
