package com.consistenthashing.config;

import com.consistenthashing.consistenthash.ConsistentHashRing;
import com.consistenthashing.model.Student;
import com.consistenthashing.service.StudentService;
import com.consistenthashing.storage.StudentStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StudentSeeder implements CommandLineRunner {

    private final StudentService studentService;
    private final StudentStore store;
    private final ConsistentHashRing ring;

    private static final List<Student> SEED = List.of(
            new Student(1001L, "Alice", "CS", 3),
            new Student(1002L, "Bob", "EC", 2),
            new Student(1003L, "Charlie", "CS", 4),
            new Student(1004L, "Diana", "ME", 1),
            new Student(1005L, "Eve", "EC", 3),
            new Student(1006L, "Frank", "CS", 2),
            new Student(1007L, "Grace", "ME", 4),
            new Student(1008L, "Henry", "EC", 1),
            new Student(1009L, "Ivy", "CS", 3),
            new Student(1010L, "Jack", "ME", 2),
            new Student(1011L, "Kate", "CS", 4),
            new Student(1012L, "Leo", "EC", 1),
            new Student(1013L, "Mia", "ME", 3),
            new Student(1014L, "Noah", "CS", 2),
            new Student(1015L, "Olivia", "EC", 4),
            new Student(1016L, "Paul", "ME", 1),
            new Student(1017L, "Quinn", "CS", 3),
            new Student(1018L, "Rose", "EC", 2),
            new Student(1019L, "Sam", "ME", 4),
            new Student(1020L, "Tina", "CS", 1)
    );

    @Override
    public void run(String... args) {
        long total = ring.getPhysicalNodes().stream()
                .mapToLong(store::countOn).sum();
        if (total > 0) {
            log.info("StudentSeeder: {} existing record(s) found; skipping seed", total);
            return;
        }
        int created = 0;
        for (Student s : SEED) {
            try {
                studentService.createStudent(s.getRollNo(), s.getName(), s.getDept(), s.getYear());
                created++;
            } catch (Exception e) {
                log.debug("Seed skip/duplicate rollNo={}: {}", s.getRollNo(), e.getMessage());
            }
        }
        log.info("StudentSeeder: seeded {} students", created);
    }
}
