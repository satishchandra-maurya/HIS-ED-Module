package in.satish.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import in.satish.binding.EligResponse;
import in.satish.service.ElgibilityDeterminationService;

@RestController
public class EligibilityDeterminationController {

	@Autowired
	private ElgibilityDeterminationService eds;
	
	@GetMapping("/elig/{caseNumber}")
	public ResponseEntity<EligResponse> elgibilityDetermine(@PathVariable Long caseNumber) {
		EligResponse eligResponse = eds.eligDetermination(caseNumber);
		
		return new ResponseEntity<> (eligResponse, HttpStatus.OK);
	}
}
