package threepc;

public enum FailType {

	BEFORE_DELIVER,   // fail immediately before delivering message  
		// descriptors: sender's number AND target number of messages (of this type) delivered
	AFTER_DELIVER,    // fail immediately after delivering certain message
		// descriptors: sender's number AND MessageType AND number of messages delivered
	BEFORE_SEND,      //individual sends only
		// descriptors: receiver's number AND MessageType AND number of messages sent
		// AND list of messages to send
	AFTER_SEND,
		// descriptors: receiver's number AND MessageType AND number of messages sent
		// AND list of messages to send
	PARTIAL_BROADCAST,
		// descriptors: processes to send to AND MessageType AND number of broadcasts sent
	ENDING_STATE,  // fail immediately before writing new state to DTlog
		// descriptors: TransactionState AND number of times in this state
	BEGINNING_STATE,     // fail immediately after writing new state to DTlog
		// descriptors: TransactionState AND number of times in this state
}
