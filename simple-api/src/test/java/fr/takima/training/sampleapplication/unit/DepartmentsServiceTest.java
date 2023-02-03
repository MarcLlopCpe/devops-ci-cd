package fr.takima.training.sampleapplication.unit;

import fr.takima.training.simpleapi.dao.DepartmentDAO;
import fr.takima.training.simpleapi.entity.Department;
import fr.takima.training.simpleapi.service.DepartmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DepartmentsServiceTest {

    @InjectMocks
    private DepartmentService departmentService;

    @Mock
    private DepartmentDAO departmentDAO;

    private Department department = Department.builder().id(1L).name("DepartementTest").build();

    @Test
    public void testGetDepartmentByName() {
        when(departmentDAO.findDepartmentByName("DepartmentTest")).thenReturn(department);
        assertEquals(department, departmentDAO.findDepartmentByName("DepartmentTest"));
    }

    @Test
    public void testGetDepartmentByNameWithNullValue() {
        assertThrows(IllegalArgumentException.class, () -> departmentService.getDepartmentByName(null));
    }

    @Test
    public void testGetDepartmentByNameWithEmptyValue() {
        assertThrows(IllegalArgumentException.class, () -> departmentService.getDepartmentByName(""));
    }
}
