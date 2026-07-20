package com.banula.tariffmanager.event;

import com.banula.tariffmanager.model.dto.HubClientInfoDTO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PartyConnectedEvent extends ApplicationEvent {

    private final HubClientInfoDTO party;

    public PartyConnectedEvent(Object source, HubClientInfoDTO party) {
        super(source);
        this.party = party;
    }
}
