package com.planner.server.domain.entity;

import com.sun.istack.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StudyCategory {

    @Id
    @NotNull
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    private String name;
    private String description;

    @OneToMany(mappedBy = "studyCategory", cascade = CascadeType.ALL)
    private List<StudyRoom> studyRooms = new ArrayList<>();

    public void addStudyRoom(StudyRoom studyRoom){
        this.studyRooms.add(studyRoom);
        studyRoom.setStudyCategory(this);
    }

    public StudyCategory(UUID id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

}
