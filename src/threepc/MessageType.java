package threepc;

public enum MessageType {
	// Everyone
	HEARTBEAT,

	// Messages from Coordinator to Participant.
	VOTE_REQ, PRE_COMMIT, COMMIT, ABORT,

	// Messages from Participant to Coordinator.
	YES, NO, ACK,

	// Message to Coordinator from outside.
	ADD, DELETE, EDIT,

	// TERMINATION related messages.
	UR_SELECTED, STATE_REQ, STATE_VALUE,

	// RECOVERY SPECIFIC MESSAGE.
	STATE_ENQUIRY, STATE_UNDECIDED, STATE_COMMIT, STATE_ABORT, STATE_RECOVERING,

	// Misc.
	PRINT_STATE, GIVE_COORDINATOR, COORDINATOR_NO,

	// To kill any process.
	DIE,
};
