package in.satish.service;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import in.satish.binding.EligResponse;
import in.satish.entity.CitizenApp;
import in.satish.entity.CoTriggers;
import in.satish.entity.DcCases;
import in.satish.entity.DcChildren;
import in.satish.entity.DcEducation;
import in.satish.entity.DcIncome;
import in.satish.entity.ElgibilityDetermination;
import in.satish.entity.PlanMaster;
import in.satish.repo.CitizenAppRepo;
import in.satish.repo.CoTriggersRepo;
import in.satish.repo.DcCasesRepo;
import in.satish.repo.DcChildrenRepo;
import in.satish.repo.DcEducationRepo;
import in.satish.repo.DcIncomeRepo;
import in.satish.repo.ElgibilityDeterminationRepo;
import in.satish.repo.PlanMasterRepo;

@Service
public class ElgibilityDeterminationServiceImpl implements ElgibilityDeterminationService {

	@Autowired
	private ElgibilityDeterminationRepo edr;
	
	@Autowired
	private CoTriggersRepo coTriggersRepo;

	@Autowired
	private DcCasesRepo dcRepo;

	@Autowired
	private PlanMasterRepo planMasterRepo;

	@Autowired
	private DcIncomeRepo dcIncomeRepo;

	@Autowired
	private CitizenAppRepo citizenAppRepo;

	@Autowired
	private DcChildrenRepo dcChildrenRepo;
	
	@Autowired
	private DcEducationRepo dcEducationRepo;

	@Override
	public EligResponse eligDetermination(Long caseNumber) {
		
		Integer planId=null;
		String planName=null;
		Integer appId=null;
		
		
	// Based on caseNumber we find the planId and appId
		Optional<DcCases> findById = dcRepo.findById(caseNumber);
		 if(findById.isPresent()) 
		 { 
			DcCases dcCaseEntity = findById.get(); 
			planId = dcCaseEntity.getPlanId(); 
			appId = dcCaseEntity.getAppId();
		 }
		 
	// based on planId find the planName 
		Optional<PlanMaster> findById2 = planMasterRepo.findById(planId); 
		if(findById2.isPresent())
		{
			PlanMaster planMaster = findById2.get();
			planName = planMaster.getPlanName();
		}
		 
		// based on appId find the citizen age 
		Optional<CitizenApp> findById1 = citizenAppRepo.findById(appId);
		Integer age = 0;
		CitizenApp citizenApp = null;
		if(findById1.isPresent()) {
		citizenApp = findById1.get();
		  // get the age 
		Date dob = citizenApp.getDob(); 
		Instant instant = dob.toInstant(); 
		ZonedDateTime atZone = instant.atZone(ZoneId.systemDefault()); 
		LocalDate dateOfBirth = atZone.toLocalDate(); 
		
		LocalDate currentDate = LocalDate.now(); 
		Period peroid = Period.between(dateOfBirth,currentDate); 
		age = peroid.getYears();
		}
		
		EligResponse eligResponse = executePlanEligCondition(caseNumber, planName, age);
		ElgibilityDetermination entity = new ElgibilityDetermination();
		BeanUtils.copyProperties(eligResponse, entity);
		
		entity.setBenefitAmt(eligResponse.getBenefitAmount());
		entity.setCaseNumber(caseNumber);
		entity.setHolderName(citizenApp.getFullName());
		entity.setHolderSsn(citizenApp.getSsn());
		
		edr.save(entity);
		
		CoTriggers coTrigger = new CoTriggers();
		coTrigger.setCaseNumber(caseNumber);
		coTrigger.setTrigStatus("pending");
		
		coTriggersRepo.save(coTrigger);
		
		return eligResponse;
	}
	
	private EligResponse executePlanEligCondition(Long caseNumber, String planName, Integer age) 
	{
		
		EligResponse response = new EligResponse();
		response.setPlanName(planName);
		DcIncome dcIncomeEntity = dcIncomeRepo.findByCaseNumber(caseNumber);
		Double empIncome=0.0;
		Double propertyIncome=0.0;
		Double employment_income=0.0;
		
		
	// SNAP condition cheking
		if("SNAP".equals(planName)) 
		  { 
			  empIncome = dcIncomeEntity.getEmpIncome();
			  propertyIncome = dcIncomeEntity.getPropertyIncome();
			  employment_income = empIncome + propertyIncome;
			  if(employment_income <= 300) 
			  {
				  response.setPlanStatus("Approved"); 
			  }
			  else 
			  { 
				  response.setPlanStatus("Denied");
				  response.setDenialReason("High Income");
			  }
		  
		
	// CCAP condition checking
		  } 
		else if("CCAP".equals(planName)){
			  
			  boolean ageCondition = true;
			  boolean childCountCondtion = false;
			  
			  List<DcChildren> childs = dcChildrenRepo.findByCaseNumber(caseNumber);
			  if(!childs.isEmpty()) {
				  childCountCondtion = true;
				  for(DcChildren child:childs) {
					  Integer childrenAge = child.getChildrenAge();
					  if(childrenAge > 16) {
						  ageCondition = false;
						  break;
					  }
				  }
			  }
			  
			  if(employment_income <= 300 && childCountCondtion && ageCondition) 
			  {
				  response.setPlanStatus("Approved");
			  }else {
				  response.setPlanStatus("Denied");
				  response.setDenialReason("Business Rule Not Satisfied..");
			  }
			  
			  
		  
		  } 
		
	// checking Medicaid Condtion
		else if("Medicaid".equals(planName)) 	
		  {
			  if(employment_income <=300 && propertyIncome==0) {
				  response.setPlanStatus("Approved");
			  }else {
				  response.setPlanStatus("Denied");
				  response.setDenialReason("Not Satisfied Business Rule");
			  }
		  } 
		
	// checking Medicare Condtion
		else if("Medicare".equals(planName)) 
		  {
			
			if(age >=65) {
				response.setPlanStatus("Approved");
			}else {
				response.setPlanStatus("Denied");
				response.setDenialReason("Age is less than 65");
			}
			
			}
			
	// checking NJW Condtion	
		else if("NJW".equals(planName)) 
		{
			DcEducation eduEntity = dcEducationRepo.findByCaseNumber(caseNumber);
			Long graduationYear = eduEntity.getGraduationYear();
			LocalDate now = LocalDate.now();
			int cuurentYear = now.getYear();
			if(employment_income <=0 && graduationYear < cuurentYear) {
				response.setPlanStatus("Approved");
			}else {
				response.setPlanStatus("Denied");
				response.setDenialReason("Rules not satisfied");
			}
			
		 }
		
		if(response.getPlanStatus().equals("Approved")) {
			response.setPlanStartDate(LocalDate.now());
			response.setPlanEndDate(LocalDate.now().plusMonths(6));
			response.setBenefitAmount(1500.00);		
		}
		
		return response;
		
	}
}
	  
	  
	  
	  
	  
	  
	  
	  
	  
	  
	  