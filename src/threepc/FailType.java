package threepc;

public enum FailType {

	BEFORE_DELIVER,   // fail immediately before delivering message  
		// descriptors: sender's number AND target number of messages (of this type) delivered
	AFTER_DELIVER,    // fail immediately after delivering certain message
		// descriptors: sender's number AND MessageType AND number of messages delivered
	BEFORE_SEND,      
		// descriptors: receiver's number AND MessageType AND number of messages sent
	AFTER_SEND,       // fail after sending a message
		// descriptors: receiver's number AND MessageType AND number of messages sent
	BEGINNING_STATE,  // fails as soon as current state starts
		// descriptors: TransactionState AND number of times in this state
	ENDING_STATE,     // fails at end of given state
		// descriptors: TransactionState AND number of times in this state
}
