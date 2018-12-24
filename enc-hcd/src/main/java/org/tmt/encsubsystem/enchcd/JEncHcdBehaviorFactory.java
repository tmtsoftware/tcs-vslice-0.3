package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.javadsl.ActorContext;
import csw.command.client.messages.TopLevelActorMessage;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.models.JCswContext;


public class JEncHcdBehaviorFactory extends JComponentBehaviorFactory {


    @Override
    public JComponentHandlers jHandlers(ActorContext<TopLevelActorMessage> ctx, JCswContext cswCtx){
        return new JEncHcdHandlers(ctx, cswCtx);
    }

}
