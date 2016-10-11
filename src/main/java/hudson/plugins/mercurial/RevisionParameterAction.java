package hudson.plugins.mercurial;

import hudson.Util;
import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Queue;
import hudson.model.Queue.QueueAction;
import hudson.model.queue.FoldableAction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


/**
 * Used as a build parameter to specify the revision to be built.
 *
 * @author Logilab, inspired from GitSCM plugin
 */
public class RevisionParameterAction extends InvisibleAction implements Serializable,QueueAction,FoldableAction {
    public final String revid;

    public RevisionParameterAction(String revid) {
        this.revid = revid;
    }

    @Override
    public String toString() {
        return super.toString()+"[revid="+revid+"]";
    }

    /**
     * Returns whether the new item should be scheduled.
     * An action should return true if the associated task is 'different enough' to warrant a separate execution.
     * from {@link QueueAction}
      */
    public boolean shouldSchedule(List<Action> actions) {
        /* Called in two cases
        1. On the action attached to an existing queued item
        2. On the action attached to the new item to add.
        Behaviour
        If actions contain a RevisionParameterAction with a matching commit to this one, we do not need to schedule
        in all other cases we do.
        */
        List<RevisionParameterAction> otherActions = Util.filter(actions,RevisionParameterAction.class);
        for (RevisionParameterAction action: otherActions) {
            if(this.revid.equals(action.revid))
                return false;
        }
        // if we get to this point there were no matching actions so a new build is required
        return true;
    }

    /**
     * Folds this Action into another action already associated with item
     * from {@link FoldableAction}
     */
    public void foldIntoExisting(Queue.Item item, Queue.Task owner, List<Action> otherActions) {
    }

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(RevisionParameterAction.class.getName());
}
