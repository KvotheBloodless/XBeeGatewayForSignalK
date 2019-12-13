package au.com.venilia.xbee_gateway_for_signalk.event;

import org.springframework.context.ApplicationEvent;

public class SwitchStatusRequestEvent extends ApplicationEvent {

    public SwitchStatusRequestEvent(final Object source) {

        super(source);
    }
}
