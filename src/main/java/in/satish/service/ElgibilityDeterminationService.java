package in.satish.service;

import in.satish.binding.EligResponse;
public interface ElgibilityDeterminationService {

	public EligResponse
	 eligDetermination(Long caseNumber);
	
}
